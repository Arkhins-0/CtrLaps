import { AuthCard } from "@/components/AuthCard";
import { RegisterForm } from "@/components/auth/RegisterForm";

export const metadata = { title: "Create an account" };

export default function Register() {
  return (
    <AuthCard title="Create an account">
      <RegisterForm />
    </AuthCard>
  );
}
